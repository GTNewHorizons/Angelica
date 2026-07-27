import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    `java-library`
    `maven-publish`
}

apply(plugin = "xyz.wagyourtail.jvmdowngrader")

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

// Compile stubs: MC class signatures needed to resolve GTNHLib's type hierarchy.
// Not included in the published jar — real MC classes are on the classpath at runtime.
sourceSets {
    create("stubs") {
        java.srcDir("src/stubs/java")
    }
}

// Exclude stubs from all output jars
tasks.withType<Jar>().configureEach {
    exclude("net/minecraft/**")
}

repositories {
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
        name = "WagYourTail"
        url = uri("https://maven.wagyourtail.xyz/releases")
        content { includeGroup("xyz.wagyourtail.jvmdowngrader") }
    }
    mavenCentral()
    mavenLocal()
}

dependencies {
    // MC compile stubs
    compileOnly(sourceSets["stubs"].output)

    // LWJGL
    compileOnly("org.lwjgl.lwjgl:lwjgl:${property("lwjglVersion")}")
    compileOnly("org.lwjgl.lwjgl:lwjgl_util:${property("lwjglVersion")}")

    compileOnly("org.ow2.asm:asm-tree:5.0.3")

    // Our Deps
    api("com.github.GTNewHorizons:GTNHLib:${property("gtnhlibVersion")}:dev")
    api("net.minecraftforge:eventbus:${property("eventbusVersion")}")

    compileOnly("org.embeddedt.celeritas:celeritas-common:${property("celeritasVersion")}") { isTransitive = false }
    implementation("org.taumc:glsl-transformation-lib:${property("glslTransformLibVersion")}") { exclude(module = "antlr4") }
    implementation("org.antlr:antlr4-runtime:${property("antlr4RuntimeVersion")}")
    implementation("org.anarres:jcpp:${property("jcppVersion")}")

    compileOnly("org.projectlombok:lombok:${property("lombokVersion")}") { isTransitive = false }
    annotationProcessor("org.projectlombok:lombok:${property("lombokVersion")}")
    compileOnly("org.jetbrains:annotations:${property("jetbrainsAnnotationsVersion")}")
    compileOnly("org.apache.logging.log4j:log4j-api:${property("log4jVersion")}")

    // Deps (normally from MC) - compile only, but needed at test runtime
    compileOnly("com.google.guava:guava:${property("guavaVersion")}")
    testRuntimeOnly("com.google.guava:guava:${property("guavaVersion")}")
    testRuntimeOnly("it.unimi.dsi:fastutil:${property("fastutilVersion")}")
    testRuntimeOnly("org.joml:joml:${property("jomlVersion")}") { isTransitive = false }

    // Test
    testImplementation(sourceSets["stubs"].output)
    testImplementation(platform("org.junit:junit-bom:${property("junitBomVersion")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.ow2.asm:asm-tree:9.7")
    testImplementation("org.lwjgl.lwjgl:lwjgl:${property("lwjglVersion")}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:${property("log4jVersion")}")
    testRuntimeOnly("org.embeddedt.celeritas:celeritas-lwjgl2-service:${property("celeritasVersion")}") { isTransitive = false }
    testRuntimeOnly("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:${property("jvmDowngraderVersion")}:downgraded-8")
    testRuntimeOnly("org.apache.commons:commons-lang3:${property("commonsLang3Version")}")
}

val depsToDowngrade by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    depsToDowngrade("net.minecraftforge:eventbus:${property("eventbusVersion")}")
    depsToDowngrade("org.embeddedt.celeritas:celeritas-common:${property("celeritasVersion")}") { isTransitive = false }
}

tasks.withType<DowngradeFiles>().configureEach {
    downgradeTo.set(JavaVersion.VERSION_1_8)
    multiReleaseOriginal.set(false)
    multiReleaseVersions.set(emptySet())
    logLevel.set("FATAL")
}

val downgradeDepsForTest by tasks.registering(DowngradeFiles::class) {
    inputCollection = depsToDowngrade
}

val downgradeMainClasses by tasks.registering(DowngradeFiles::class) {
    inputCollection = sourceSets["main"].output.classesDirs.plus(sourceSets["stubs"].output.classesDirs)
    classpath = sourceSets["main"].compileClasspath
    dependsOn(tasks.named("classes"), tasks.named("stubsClasses"))
}

val downgradeTestClasses by tasks.registering(DowngradeFiles::class) {
    inputCollection = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].compileClasspath
    sourceSets["test"].output.classesDirs.files.forEach { outputs.dir(temporaryDir.resolve(it.name)) }
    dependsOn(tasks.named("testClasses"))
}

val GL_TASK_TAGS = mapOf(
    "glCompatTest" to "gl-compat",
    "glCoreTest" to "gl-core",
    "glSharedTest" to "gl-shared",
)

fun Test.configureGlsmJava8() {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
        if (System.getProperty("os.name").lowercase().contains("mac")
            && System.getProperty("os.arch") == "aarch64") {
            vendor = JvmVendorSpec.AZUL
        }
    }

    dependsOn(downgradeMainClasses, downgradeTestClasses, downgradeDepsForTest)

    val mainClassesDirs = sourceSets["main"].output.classesDirs
        .plus(sourceSets["stubs"].output.classesDirs)
    val testClassesDirs = sourceSets["test"].output.classesDirs

    val downgradedTest = files(downgradeTestClasses.map { it.outputCollection })
    val downgradedMain = files(downgradeMainClasses.map { it.outputCollection })
    val downgradedDeps = files(downgradeDepsForTest.map { it.outputCollection })

    setTestClassesDirs(downgradedTest)
    classpath = downgradedTest
        .plus(downgradedMain)
        .plus(downgradedDeps)
        .plus(sourceSets["test"].runtimeClasspath.minus(mainClassesDirs).minus(testClassesDirs).minus(depsToDowngrade))

    val extractNatives = rootProject.tasks.named("extractNatives2")
    dependsOn(extractNatives)
    jvmArgs("-Djava.library.path=${extractNatives.get().property("destinationFolder").let { (it as DirectoryProperty).asFile.get().path }}")
}

tasks.test {
    useJUnitPlatform {
        excludeTags = GL_TASK_TAGS.values.toSet()
    }
    configureGlsmJava8()
}

val glTestTasks = GL_TASK_TAGS.map { (taskName, tag) ->
    tasks.register<Test>(taskName) {
        description = "Runs the $tag GL tests in their own JVM, so they get their own Display and GL profile."
        group = "verification"
        useJUnitPlatform {
            includeTags = setOf(tag)
        }
        configureGlsmJava8()
    }
}

(listOf(tasks.test) + glTestTasks).zipWithNext { earlier, later ->
    later.configure { mustRunAfter(earlier) }
}
tasks.test { finalizedBy(glTestTasks) }

val verifyTestsRan by tasks.registering {
    dependsOn(tasks.test, glTestTasks)
}
(listOf(tasks.test) + glTestTasks).forEach { testTask ->
    val name = testTask.name
    val verify = tasks.register("verify${name.replaceFirstChar { it.uppercase() }}Ran") {
        val resultsDir = layout.buildDirectory.dir("test-results/$name")
        dependsOn(testTask)
        doLast {
            val dir = resultsDir.get().asFile
            val xmls = dir.listFiles { f -> f.name.startsWith("TEST-") && f.name.endsWith(".xml") } ?: emptyArray()
            check(xmls.isNotEmpty()) {
                ":glsm:$name produced no TEST-*.xml in $dir - test task likely went NO-SOURCE"
            }
        }
    }
    verifyTestsRan.configure { dependsOn(verify) }
}
tasks.check { dependsOn(verifyTestsRan) }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "com.gtnewhorizons.angelica"
            artifactId = "glsm"
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
