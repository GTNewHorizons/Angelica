plugins {
    `java-library`
}

apply(from = rootProject.file("experimental-surround/surround.project.gradle.kts"))

val mixinProviderSpec = rootProject.extra["mixinProviderSpec"] as String

val refmapProbe = sourceSets.create("refmapProbe")

dependencies {
    compileOnly(mixinProviderSpec)
    testImplementation(mixinProviderSpec)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":experimental-surround-runtime"))
    testRuntimeOnly(libs.junit.platform.launcher)
    "refmapProbeCompileOnly"(mixinProviderSpec)
    "refmapProbeCompileOnly"(project(":experimental-surround-runtime"))
    "refmapProbeAnnotationProcessor"(mixinProviderSpec)
    "refmapProbeAnnotationProcessor"(sourceSets.main.get().output)
}

val probeRefmaps = layout.buildDirectory.dir("refmap-probe")
val probeSrg = file("src/refmapProbe/mappings.srg")
val surroundAp = "com.gtnewhorizons.angelica.experimental.surround.ap.SurroundProcessor"
val mixinAps = "org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets"

fun JavaCompile.probe(name: String, processors: String?) {
    val refmap = probeRefmaps.map { it.file("refmap-probe/$name.json") }
    val srg = probeSrg
    javaCompiler = javaToolchains.compilerFor { languageVersion = JavaLanguageVersion.of(25) }
    outputs.file(refmap)
    inputs.file(srg)
    options.compilerArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-proc:only", "-AoutRefMapFile=" + refmap.get().asFile.absolutePath,
            "-AreobfSrgFile=" + srg.absolutePath, "-AdefaultObfuscationEnv=searge") +
            (if (processors == null) emptyList() else listOf("-processor", processors))
    })
}

fun JavaCompile.probeOrdered(name: String, classesDir: String, processors: String) {
    source = refmapProbe.java
    classpath = refmapProbe.compileClasspath
    options.annotationProcessorPath = refmapProbe.annotationProcessorPath
    destinationDirectory = layout.buildDirectory.dir("classes/$classesDir")
    probe(name, processors)
}

tasks.named<JavaCompile>(refmapProbe.compileJavaTaskName) { probe("discovered", null) }
val probeSurroundFirst by tasks.registering(JavaCompile::class) { probeOrdered("surround-first", "refmapProbeFirst", "$surroundAp,$mixinAps") }
val probeSurroundLast by tasks.registering(JavaCompile::class) { probeOrdered("surround-last", "refmapProbeLast", "$mixinAps,$surroundAp") }

sourceSets.test { resources.srcDir(files(probeRefmaps).builtBy(refmapProbe.compileJavaTaskName, probeSurroundFirst, probeSurroundLast)) }

tasks.named<Test>("test") {
    useJUnitPlatform()
}
