import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles

plugins {
    `java-library`
}

apply(plugin = "xyz.wagyourtail.jvmdowngrader")

apply(from = rootProject.file("experimental-surround/surround.project.gradle.kts"))

// Required by RFG consumers (runClient17 java17Dependencies) to pick a variant.
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

val mixinProviderSpec = rootProject.extra["mixinProviderSpec"] as String

dependencies {
    compileOnly(mixinProviderSpec)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(mixinProviderSpec)
    testImplementation(libs.launchwrapper)
    testImplementation(libs.commons.io)
    testImplementation(libs.guava)
    testImplementation(libs.log4j.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.jvmdowngrader.java.api) { artifact { classifier = "downgraded-8" } }
}

repositories {
    maven {
        name = "WagYourTail"
        url = uri("https://maven.wagyourtail.xyz/releases")
        content { includeGroup("xyz.wagyourtail.jvmdowngrader") }
    }
}

tasks.withType<DowngradeFiles>().configureEach {
    downgradeTo.set(JavaVersion.VERSION_1_8)
    multiReleaseOriginal.set(false)
    multiReleaseVersions.set(emptySet())
    logLevel.set("FATAL")
}

fun SourceSet.registerDowngrade(taskName: String) = tasks.register<DowngradeFiles>(taskName) {
    inputCollection = output.classesDirs
    classpath = compileClasspath
    output.classesDirs.files.forEach { outputs.dir(temporaryDir.resolve(it.name)) }
    dependsOn(tasks.named(classesTaskName))
}

val downgradeMainClasses = sourceSets["main"].registerDowngrade("downgradeMainClasses")
val downgradeTestClasses = sourceSets["test"].registerDowngrade("downgradeTestClasses")

val goldenDir = layout.projectDirectory.dir("src/test/resources/golden").asFile.absolutePath

// Runs on Java 8 against the JVMDowngrader output, the bytecode production ships.
fun Test.configureSurroundTest(runDirName: String) {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(8)
        if (System.getProperty("os.name").lowercase().contains("mac") && System.getProperty("os.arch") == "aarch64") {
            vendor = JvmVendorSpec.AZUL
        }
    }
    dependsOn(downgradeMainClasses, downgradeTestClasses)
    val downgradedTest = files(downgradeTestClasses.map { it.outputCollection })
    testClassesDirs = downgradedTest
    classpath = downgradedTest
        .plus(files(downgradeMainClasses.map { it.outputCollection }))
        .plus(sourceSets["test"].runtimeClasspath.minus(sourceSets["main"].output.classesDirs).minus(sourceSets["test"].output.classesDirs))
    val dir = layout.buildDirectory.dir(runDirName).get().asFile
    workingDir = dir
    systemProperty("surround.golden.dir", goldenDir)
    systemProperty("mixin.debug.verify", "true")
    val javac = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dsurround.javac=" + javac.get().executablePath.asFile.absolutePath) })
    doFirst { dir.mkdirs() }
}

fun Test.configureUntaggedSurroundTest(runDirName: String, vararg systemProps: Pair<String, String>) {
    useJUnitPlatform { excludeTags("mixinextras") }
    for ((key, value) in systemProps) {
        systemProperty(key, value)
    }
    configureSurroundTest(runDirName)
}

fun Test.configureTaggedSurroundTest(runDirName: String, tag: String, vararg systemProps: Pair<String, String>) {
    useJUnitPlatform { includeTags(tag) }
    for ((key, value) in systemProps) {
        systemProperty(key, value)
    }
    configureSurroundTest(runDirName)
}

tasks.named<Test>("test") {
    configureUntaggedSurroundTest("test-run")
}

val mixinExtrasTest by tasks.registering(Test::class) {
    configureTaggedSurroundTest(
        "test-run-mixinextras",
        "mixinextras",
        "surround.test.mixinextras" to "true"
    )
}

val mixinExtrasAdditionsFirstTest by tasks.registering(Test::class) {
    configureTaggedSurroundTest(
        "test-run-mixinextras-additions-first",
        "mixinextras",
        "surround.test.mixinextras" to "true",
        "surround.test.additionsFirst" to "true"
    )
}

val obfTest by tasks.registering(Test::class) {
    configureUntaggedSurroundTest("test-run-obf", "surround.test.obf" to "true")
    filter { excludeTestsMatching("*.RejectionTest") }
}

tasks.named("check") {
    dependsOn(mixinExtrasTest, mixinExtrasAdditionsFirstTest, obfTest)
}
