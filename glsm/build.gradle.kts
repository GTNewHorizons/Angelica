import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    `java-library`
    `java-test-fixtures`
    `maven-publish`
}

apply(plugin = "xyz.wagyourtail.jvmdowngrader")

version = rootProject.version

val lwjglNatives: String by extra

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

// RFG variant attributes: the root Angelica project uses RetroFuturaGradle which expects
// these attributes on dependencies. Without them, Gradle can't resolve the subproject variant.
val rfgObfAttr = Attribute.of("com.gtnewhorizons.retrofuturagradle.obfuscation", String::class.java)
val rfgTransformedAttr = Attribute.of("rfgDeobfuscatorTransformed", Boolean::class.javaObjectType)

configurations {
    listOf("apiElements", "runtimeElements", "testFixturesApiElements", "testFixturesRuntimeElements").forEach { name ->
        named(name) {
            attributes {
                attribute(rfgObfAttr, "mcp")
                attribute(rfgTransformedAttr, true)
            }
        }
    }
}

// Compile stubs: MC class signatures needed to resolve GTNHLib's type hierarchy.
// Not included in the published jar - real MC classes are on the classpath at runtime.
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
    maven {
        name = "Sonatype Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "Maven Central Snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }

    mavenLocal()
    mavenCentral()
    mavenLocal()
}

dependencies {
    // MC compile stubs
    compileOnly(sourceSets["stubs"].output)

    // LWJGL
    compileOnly(libs.lwjgl2)
    compileOnly(libs.lwjgl2.util)

    compileOnly(libs.asm.tree.compile)

    // Our Deps
    api(libs.gtnhlib) { artifact { classifier = "dev" } }
    api(libs.eventbus)

    compileOnly(libs.celeritas.common) { isTransitive = false }
    api(libs.glsl.transformation.lib) { exclude(module = "antlr4") }
    api(libs.antlr4.runtime)
    implementation(libs.jcpp)

    // shaderc + SPIRV-Cross for the SpirvShaderTranslator (GLSL -> SPIR-V -> GLSL ES).
    // TODO: Remove
    compileOnly(libs.lwjgl3.shaderc)
    compileOnly(libs.lwjgl3.spvc)
    testImplementation(libs.lwjgl3.shaderc)
    testImplementation(libs.lwjgl3.spvc)
    // Host natives so the JNI wrapper can find libshaderc / libspirv-cross / liblwjgl.
    testRuntimeOnly(libs.lwjgl3.shaderc) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl3.spvc) { artifact { classifier = lwjglNatives } }
    testRuntimeOnly(libs.lwjgl3)
    testRuntimeOnly(libs.lwjgl3) { artifact { classifier = lwjglNatives } }
    // @Lwjgl3Aware annotation
    compileOnly(libs.lwjgl3ify) { artifact { classifier = "dev" }; isTransitive = false }

    compileOnly(libs.lombok) { isTransitive = false }
    annotationProcessor(libs.lombok)
    compileOnly(libs.annotations)
    compileOnly(libs.log4j.api)

    // Deps (normally from MC) - compile only, but needed at test runtime
    compileOnly(libs.guava)
    testRuntimeOnly(libs.guava)
    testRuntimeOnly(libs.fastutil)
    testRuntimeOnly(libs.joml) { isTransitive = false }

    testFixturesCompileOnly(sourceSets["stubs"].output)
    testFixturesApi(platform(libs.junit.bom))
    testFixturesApi(libs.junit.jupiter.api)
    testFixturesApi(libs.lwjgl2)

    // Test
    testImplementation(sourceSets["stubs"].output)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.asm.tree.test)
    testImplementation(libs.lwjgl2)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.log4j.core)
    testImplementation(libs.celeritas.common) { isTransitive = false }
    testRuntimeOnly(libs.celeritas.lwjgl2.service) { isTransitive = false }
    testRuntimeOnly(libs.jvmdowngrader.java.api) { artifact { classifier = "downgraded-8" } }
    testRuntimeOnly(libs.commons.lang3)
}

val depsToDowngrade by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    depsToDowngrade(libs.eventbus)
    depsToDowngrade(libs.celeritas.common) { isTransitive = false }
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
    (sourceSets["main"].output.classesDirs.files + sourceSets["stubs"].output.classesDirs.files)
        .forEach { outputs.dir(temporaryDir.resolve(it.name)) }
    dependsOn(tasks.named("classes"), tasks.named("stubsClasses"))
}

val downgradeTestFixturesClasses by tasks.registering(DowngradeFiles::class) {
    inputCollection = sourceSets["testFixtures"].output.classesDirs
    classpath = sourceSets["testFixtures"].compileClasspath
    sourceSets["testFixtures"].output.classesDirs.files.forEach { outputs.dir(temporaryDir.resolve(it.name)) }
    dependsOn(tasks.named("testFixturesClasses"))
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

    dependsOn(downgradeMainClasses, downgradeTestClasses, downgradeTestFixturesClasses, downgradeDepsForTest)

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
        .plus(files(downgradeTestFixturesClasses.map { it.outputCollection }))
        .plus(sourceSets["test"].runtimeClasspath.minus(mainClassesDirs).minus(testClassesDirs)
            .minus(sourceSets["testFixtures"].output.classesDirs).minus(depsToDowngrade))

    val extractNatives = rootProject.tasks.named("extractNatives2")
    dependsOn(extractNatives)
    jvmArgs("-Djava.library.path=${extractNatives.get().property("destinationFolder").let { (it as DirectoryProperty).asFile.get().path }}")
}

tasks.test {
    useJUnitPlatform {
        excludeTags = GL_TASK_TAGS.values.toSet() + "lwjgl3"
    }
    filter {
        excludeTestsMatching("com.gtnewhorizons.angelica.glsm.shader.SpirvShaderTranslator*Test")
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

val spirvTest by tasks.registering(Test::class) {
    description = "Runs SpirvShaderTranslator* tests against native shaderc + spvc on Java 21."
    group = "verification"
    useJUnitPlatform()

    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath.filter { file ->
        // Strip the LWJGL2 jars - they collide with LWJGL3's sealed org.lwjgl package.
        !file.name.startsWith("lwjgl-2.") && !file.name.startsWith("lwjgl_util-") && !file.name.startsWith("lwjgl-platform-")
    }

    filter {
        includeTestsMatching("com.gtnewhorizons.angelica.glsm.shader.SpirvShaderTranslator*Test")
        includeTestsMatching("com.gtnewhorizons.angelica.glsm.ffp.FFPUniformBlockSpirvLayoutTest")
    }

    // libshaderc.so / libspirv-cross.so are extracted by extractNatives3.
    val extractNatives3 = rootProject.tasks.named("extractNatives3")
    dependsOn(extractNatives3)
    jvmArgs(
        "-Djava.library.path=${extractNatives3.get().property("destinationFolder").let { (it as DirectoryProperty).asFile.get().path }}",
        "--enable-native-access=ALL-UNNAMED"
    )

    testLogging {
        showStandardStreams = true
    }
}

tasks.named("check") { dependsOn(spirvTest) }

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
